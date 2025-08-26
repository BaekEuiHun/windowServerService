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

    public WasController(SshService ssh, WasService was) {
        this.ssh = ssh;
        this.was = was;
    }

    /** 2-1) 파일 업로드 (멀티파트) */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WasDtos.StepResult> upload(@RequestPart MultipartFile file,
                                                     @RequestParam String ip,
                                                     @RequestParam String username,
                                                     @RequestParam String password,
                                                     @RequestParam(defaultValue="/WAS.tar") String remotePath) {
        Instant t0 = Instant.now();
        Session s = null;
        try {
            // SSH 세션 열기
            s = ssh.openSession(ip, username, password, 8000);
            // SFTP를 이용해 원격 서버에 파일 업로드
            var up = ssh.uploadFile(s, file.getInputStream(), remotePath);
            // 업로드 성공 응답
            return ResponseEntity.ok(new WasDtos.StepResult("업로드", true,
                    "uploaded to " + remotePath + " ("+file.getOriginalFilename()+")",
                    Duration.between(t0, Instant.now()).toMillis()));
        } catch (JSchException | SftpException | java.io.IOException e) {
            // 업로드 실패 응답
            return ResponseEntity.ok(new WasDtos.StepResult("업로드", false, e.getMessage(),
                    Duration.between(t0, Instant.now()).toMillis()));
        } finally {
            if (s != null && s.isConnected()) s.disconnect();
        }
    }

    /** 2-2) 압축해제 + 패키지 설치 + 방화벽 80 허용 + nginx 시작 */
    @PostMapping(value = "/setup", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WasDtos.SetupResponse> setup(@RequestBody WasDtos.SetupRequest req) {
        WasDtos.SetupResponse resp = new WasDtos.SetupResponse(req.ip, req.username);
        // 압축 해제
        resp.add(was.extractTar(req.ip, req.username, req.password, req.wasTarPath, req.extractTo));
        // 패키지 설치
        resp.add(was.installPackages(req.ip, req.username, req.password, req.getPackagesOrDefault()));
        // 방화벽 80 허용
        resp.add(was.openFirewallHttp80(req.ip, req.username, req.password));
        // nginx 시작(예시: varnish도 필요하면 한 줄 더)
        resp.add(was.enableAndStart(req.ip, req.username, req.password, "nginx"));

        return ResponseEntity.ok(resp);
    }

    /** 2-3) 원샷: 업로드 + 압축해제 + 설치 + 방화벽 + nginx 시작 */
    @PostMapping(value = "/setup-all", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WasDtos.SetupResponse> setupAll(@RequestPart MultipartFile file,
                                                          @RequestParam String ip,
                                                          @RequestParam String username,
                                                          @RequestParam String password,
                                                          @RequestParam(defaultValue="/WAS.tar") String remotePath,
                                                          @RequestParam(defaultValue="/") String extractTo) {
        WasDtos.SetupResponse resp = new WasDtos.SetupResponse(ip, username);

        // 1) 업로드
        Session s = null;
        try {
            s = ssh.openSession(ip, username, password, 8000);
            ssh.uploadFile(s, file.getInputStream(), remotePath);
            resp.add(new WasDtos.StepResult("업로드", true, "uploaded to " + remotePath, 0));
        } catch (Exception e) {
            resp.add(new WasDtos.StepResult("업로드", false, e.getMessage(), 0));
            return ResponseEntity.ok(resp);
        } finally {
            if (s != null && s.isConnected()) s.disconnect();
        }

        // 2) 압축 해제
        resp.add(was.extractTar(ip, username, password, remotePath, extractTo));
        // 3) 패키지 설치
        resp.add(was.installPackages(ip, username, password, null));
        // 4) 방화벽 80 허용
        resp.add(was.openFirewallHttp80(ip, username, password));
        // 5) nginx 시작
        resp.add(was.enableAndStart(ip, username, password, "nginx"));

        return ResponseEntity.ok(resp);
    }
}