package InnotiumProject1.demo.ssh;


import com.jcraft.jsch.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

@Component
//SSH 연결과 관련된 기능을 모아둔 서비스 클래스
public class SshService {

    //SSH 연결을 여는 메서드
    public Session openSession(String host, String username, String password, int timeoutMs) throws JSchException {
        JSch jsch = new JSch();
        Session session = jsch.getSession(username, host, 22);
        session.setPassword(password);
        java.util.Properties config = new java.util.Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.connect(timeoutMs);
        return session;
    }

    // 원격 서버에 특정 명령어를 실행하고 결과를 가져오는 메서드
    public ExecResult runCommand(Session session, String command) throws JSchException, IOException {
        Instant t0 = Instant.now();
        // 1. Exec 채널 열기
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        // 2. 출력/에러 스트림 준비
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        channel.setOutputStream(out);
        channel.setErrStream(err);
        // 3. 명령 실행
        channel.connect();
        // 4. 명령이 끝날 때까지 대기
        while (!channel.isClosed()) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }
        }
        // 5. 종료 코드 가져오기
        int code = channel.getExitStatus();
        channel.disconnect();
        Duration d = Duration.between(t0, Instant.now());
        // 6. 결과 객체 반환 -> 리눅스에서 0은 성공을 의미, 0이 아닌 값은 실패를 의미
        // OUT : 표준 출력
        // ERR : 표준 에러
        return new ExecResult(command, code == 0, code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8), d.toMillis());
    }

    private static String shSingleQuote(String s) {
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }

    public ExecResult execSudo(Session session, String password, String rawCmd)
            throws JSchException, IOException {
        // 비번/명령 각각을 shell-safe 하게 싱글쿼트로 감싼다
        String pwQ = shSingleQuote(password);
        String cmdQ = shSingleQuote(rawCmd);

        // 전체를 bash -lc '<여기에 실제 커맨드 한 줄>'
        // 내부에서는: echo '<pw>' | sudo -S -p '' bash -lc '<rawCmd>'
        String full = "bash -lc " + shSingleQuote(
                "echo " + pwQ + " | sudo -S -p '' bash -lc " + cmdQ
        );

        // 이제 full 은 따옴표 균형이 맞는 하나의 안전한 커맨드입니다.
        return runCommand(session, full);
    }

    public ExecResult uploadFile(Session session, InputStream src, String remotePath)
            throws JSchException, SftpException, IOException {
        Instant t0 = Instant.now();
        ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
        sftp.connect();
        try (src) {
            sftp.put(src, remotePath);
        } finally {
            sftp.disconnect();
        }


        // 업로드 후 실제 서버에 존재하는지 확인
        String verify = "test -f " + remotePath + " && echo 'OK' || echo 'MISSING'";
        ExecResult check = runCommand(session, verify);
        boolean success = check.stdout.contains("OK");

        Duration d = Duration.between(t0, Instant.now());
        return new ExecResult("SFTP upload -> " + remotePath, success,
                success ? 0 : 1,
                success ? "uploaded" : "upload failed: " + check.stdout,
                check.stderr, d.toMillis());
    }


    public static class ExecResult {
        public final String command;
        public final boolean success;
        public final int exitCode;
        public final String stdout;
        public final String stderr;
        public final long durationMs;


        public ExecResult(String command, boolean success, int exitCode, String stdout, String stderr, long durationMs) {
            this.command = command;
            this.success = success;
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.durationMs = durationMs;
        }
    }
}
