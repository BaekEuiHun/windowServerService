package InnotiumProject1.demo.precheck.controller;

import InnotiumProject1.demo.precheck.dto.WasDtos;
import InnotiumProject1.demo.precheck.service.WasService;
import InnotiumProject1.demo.ssh.SshService;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/api/was")
public class WasController {

    private final SshService ssh;
    private final WasService was;
    private String fixedHomeTarPath(String username) {
        return "root".equals(username) ? "/root/WAS.tar" : "/home/" + username + "/WAS.tar";
    }

    public WasController(SshService ssh, WasService was) {
        this.ssh = ssh;
        this.was = was;
    }

    /** 2-1) 파일 업로드 (멀티파트) */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WasDtos.StepResult> upload(@RequestPart MultipartFile file,
                                                     @RequestParam String ip,
                                                     @RequestParam String username,
                                                     @RequestParam String password) {
        Instant t0 = Instant.now();
        Session s = null;
        try {
            s = ssh.openSession(ip, username, password, 8000);

            // ✅ 무조건 /home/{username}/WAS.tar (root는 /root/WAS.tar)
            String remotePath = fixedHomeTarPath(username);

            ssh.uploadFile(s, file.getInputStream(), remotePath);
            return ResponseEntity.ok(new WasDtos.StepResult(
                    "업로드", true,
                    "uploaded to " + remotePath + " (" + file.getOriginalFilename() + ")",
                    Duration.between(t0, Instant.now()).toMillis()
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(new WasDtos.StepResult(
                    "업로드", false, e.getMessage(),
                    Duration.between(t0, Instant.now()).toMillis()
            ));
        } finally {
            if (s != null && s.isConnected()) s.disconnect();
        }
    }

    /** 2-2) 압축해제 + 패키지 설치 + 방화벽 80 허용 + nginx 시작 */
    @PostMapping(value = "/setup", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WasDtos.SetupResponse> setup(@RequestBody WasDtos.SetupRequest req) {
        WasDtos.SetupResponse resp = new WasDtos.SetupResponse(req.ip, req.username);

        // ✅ wasTarPath 미지정 시, 고정 경로로 설정
        if (req.wasTarPath == null || req.wasTarPath.isBlank()) {
            req.wasTarPath = "root".equals(req.username)
                    ? "/root/WAS.tar"
                    : "/home/" + req.username + "/WAS.tar";
        }

        resp.add(was.extractTar(req.ip, req.username, req.password, req.wasTarPath, req.extractTo));
        resp.add(was.installPackages(req.ip, req.username, req.password, req.getPackagesOrDefault()));
        resp.add(was.openFirewallHttp80(req.ip, req.username, req.password));
        resp.add(was.enableAndStart(req.ip, req.username, req.password, "nginx"));

        return ResponseEntity.ok(resp);
    }

    /** 2-3) 원샷: 업로드 + 압축해제 + 설치 + 방화벽 + nginx 시작 */
    // ✅ 버튼 한 번으로 끝나는 원샷 배포 엔드포인트
    @PostMapping(value = "/deploy-oneclick", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WasDtos.SetupResponse> deployOneclick(
            @RequestPart MultipartFile file,
            @RequestParam String ip,
            @RequestParam String username,
            @RequestParam String password
    ) {
        // 기본 경로(사용자 홈/WAS.tar, 루트 보호 경로/opt/app)
        final String homeTar = "root".equals(username) ? "/root/WAS.tar" : "/home/" + username + "/WAS.tar";
        final String destDir = "/opt/app";           // 🔧 고객사 표준 배치 경로
        final String destTar = destDir + "/WAS.tar"; // 압축파일 최종 위치

        WasDtos.SetupResponse resp = new WasDtos.SetupResponse(ip, username);
        Session s = null;
        try {
            // 1) SSH 연결
            s = ssh.openSession(ip, username, password, 8000);

            // 2) 업로드(일반 권한) → 무조건 /home/{username}/WAS.tar
            ssh.uploadFile(s, file.getInputStream(), homeTar);
            resp.add(new WasDtos.StepResult("업로드", true, "uploaded to " + homeTar, 0));

            // 3) 관리자 권한으로 목적지 준비 + 이동
            //    (디렉토리 만들고, 홈에서 /opt/app으로 옮기고, 소유권 조정은 선택)
            var mv = ssh.execSudo(s, password, String.join(" && ",
                    "mkdir -p " + destDir,
                    "mv " + homeTar + " " + destTar,
                    "chown " + username + ":" + username + " " + destTar
            ));
            resp.add(new WasDtos.StepResult("배치 경로 준비/이동", mv.success,
                    mv.success ? ("moved to " + destTar) : mv.stderr, 0));
            if (!mv.success) return ResponseEntity.ok(resp);

            // 4) 압축해제(관리자 권한) – /opt/app 안에 풀기
            resp.add(was.extractTar(ip, username, password, destTar, destDir));

            // 5) 필수 패키지 설치(관리자 권한) – Ubuntu/Rocky 자동 감지
            resp.add(was.installPackages(ip, username, password, null));

            // 6) 방화벽 80 허용(관리자 권한) – ufw/firewalld 자동 분기
            resp.add(was.openFirewallHttp80(ip, username, password));

            // 7) 서비스 시작(관리자 권한) – 예시는 nginx
            resp.add(was.enableAndStart(ip, username, password, "nginx"));

            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.add(new WasDtos.StepResult("오류", false, e.getMessage(), 0));
            return ResponseEntity.ok(resp);
        } finally {
            if (s != null && s.isConnected()) s.disconnect();
        }
    }
}