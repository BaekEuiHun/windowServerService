package InnotiumProject1.demo.precheck.service;

import InnotiumProject1.demo.precheck.dto.WasDtos;
import InnotiumProject1.demo.ssh.SshService;
import com.jcraft.jsch.Session;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WasService {
    private final SshService ssh;

    public WasService(SshService ssh) {
        this.ssh = ssh;
    }

    /** dnf > yum > apt-get 감지 후 리턴 */
    /** 현재 서버의 패키지 매니저(dnf, yum, apt-get) 자동 감지 */
    private String detectPkgManager(Session s) throws Exception {
        var r = ssh.runCommand(s, "bash -lc 'command -v dnf || command -v yum || command -v apt-get || true'");
        String bin = r.stdout.trim();
        if (bin.contains("dnf")) return "dnf";
        if (bin.contains("yum")) return "yum";
        if (bin.contains("apt-get")) return "apt-get";
        return "none";
    }

    /** 1) 업로드된 tar 파일 압축 해제 */
    public WasDtos.StepResult extractTar(String ip, String user, String pw, String tarPath, String extractTo) {
        Instant t0 = Instant.now();
        Session s = null;
        try {
            s = ssh.openSession(ip, user, pw, 8000);
            var r = ssh.execSudo(s, pw, "cd '"+extractTo+"' && tar -xvf '"+tarPath+"'");
            String msg = r.success ? r.stdout : (r.stdout + "\n" + r.stderr);
            return new WasDtos.StepResult("압축 해제", r.success, msg, Duration.between(t0, Instant.now()).toMillis());
        } catch (Exception e) {
            return new WasDtos.StepResult("압축 해제", false, e.getMessage(), Duration.between(t0, Instant.now()).toMillis());
        } finally {
            if (s != null && s.isConnected()) s.disconnect();
        }
    }

    /** 2) 필요한 패키지 설치 */
    public WasDtos.StepResult installPackages(String ip, String user, String pw, List<String> packages) {
        Instant t0 = Instant.now();
        Session s = null;
        try {
            s = ssh.openSession(ip, user, pw, 8000);
            String pm = detectPkgManager(s);
            if (pm.equals("none")) {
                return new WasDtos.StepResult("패키지 설치", false, "패키지 매니저를 찾을 수 없습니다.", Duration.between(t0, Instant.now()).toMillis());
            }
            // Rocky 대비: EPEL(없으면 설치 시도), 이후 패키지 설치
            // 설치할 패키지 목록 준비
            String list = (packages == null || packages.isEmpty())
                    ? new WasDtos.SetupRequest().getPackagesOrDefault().stream().collect(Collectors.joining(" "))
                    : packages.stream().collect(Collectors.joining(" "));

            String cmd;
            if (pm.equals("apt-get")) {
                // Ubuntu 계열
                cmd = "apt-get update && apt-get -y install " + list;
            } else {
                // dnf/yum 공통
                // Rocky 계열: epel-release 먼저 설치 시도
                cmd = "yum -y install epel-release || dnf -y install epel-release || true; " +
                        "yum -y install " + list + " || dnf -y install " + list;
            }

            var r = ssh.execSudo(s, pw, cmd);
            String msg = r.success ? r.stdout : (r.stdout + "\n" + r.stderr);
            return new WasDtos.StepResult("패키지 설치", r.success, msg, Duration.between(t0, Instant.now()).toMillis());
        } catch (Exception e) {
            return new WasDtos.StepResult("패키지 설치", false, e.getMessage(), Duration.between(t0, Instant.now()).toMillis());
        } finally {
            if (s != null && s.isConnected()) s.disconnect();
        }
    }

    /** 3) 방화벽에서 HTTP(80) 허용 */
    public WasDtos.StepResult openFirewallHttp80(String ip, String user, String pw) {
        Instant t0 = Instant.now();
        Session s = null;
        try {
            s = ssh.openSession(ip, user, pw, 8000);
            // Ubuntu: ufw 있으면 허용, Rocky: firewalld면 허용
            String cmd =
                    "if command -v ufw >/dev/null 2>&1; then " +
                            "  (ufw status || true) >/dev/null 2>&1; ufw allow 80/tcp || true; " +
                            "elif systemctl is-active firewalld >/dev/null 2>&1; then " +
                            "  firewall-cmd --add-service=http --permanent && firewall-cmd --reload; " +
                            "fi";
            var r = ssh.execSudo(s, pw, cmd);
            String msg = r.success ? r.stdout : (r.stdout + "\n" + r.stderr);
            return new WasDtos.StepResult("방화벽(HTTP 80) 허용", r.success, msg, Duration.between(t0, Instant.now()).toMillis());
        } catch (Exception e) {
            return new WasDtos.StepResult("방화벽(HTTP 80) 허용", false, e.getMessage(), Duration.between(t0, Instant.now()).toMillis());
        } finally {
            if (s != null && s.isConnected()) s.disconnect();
        }
    }

    /** 4) 지정된 서비스(systemd) 활성화 및 시작 */
    public WasDtos.StepResult enableAndStart(String ip, String user, String pw, String serviceName) {
        Instant t0 = Instant.now();
        Session s = null;
        try {
            s = ssh.openSession(ip, user, pw, 8000);
            var r = ssh.execSudo(s, pw, "systemctl enable "+serviceName+" || true ; systemctl restart "+serviceName+" || systemctl start "+serviceName);
            String msg = r.success ? r.stdout : (r.stdout + "\n" + r.stderr);
            return new WasDtos.StepResult(serviceName + " 서비스 시작", r.success, msg, Duration.between(t0, Instant.now()).toMillis());
        } catch (Exception e) {
            return new WasDtos.StepResult(serviceName + " 서비스 시작", false, e.getMessage(), Duration.between(t0, Instant.now()).toMillis());
        } finally {
            if (s != null && s.isConnected()) s.disconnect();
        }
    }
}
