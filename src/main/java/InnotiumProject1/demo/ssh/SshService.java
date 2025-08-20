package InnotiumProject1.demo.ssh;


import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

    public ExecResult exec(Session session, String command) throws JSchException, IOException {
        Instant t0 = Instant.now();
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        channel.setOutputStream(out);
        channel.setErrStream(err);
        channel.connect();
        while (!channel.isClosed()) {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }
        int code = channel.getExitStatus();
        channel.disconnect();
        Duration d = Duration.between(t0, Instant.now());
        return new ExecResult(command, code == 0, code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8), d.toMillis());
    }

    public ExecResult execSudo(Session session, String password, String rawCmd) throws JSchException, IOException {
        // 단일 인용부호 이스케이프
        String safe = rawCmd.replace("'", "'\"'\"'");
        String sudo = "bash -lc 'echo \"" + password + "\" | sudo -S -p \"\" bash -lc \'" + safe + "\'''";
        return exec(session, sudo);
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
