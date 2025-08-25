package InnotiumProject1.demo.precheck.controller;

import InnotiumProject1.demo.precheck.dto.PrecheckResponse;
import InnotiumProject1.demo.precheck.service.PrecheckService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")

public class PrecheckController {

    private final PrecheckService service;

    public PrecheckController(PrecheckService service) {
        this.service = service;
    }

    @GetMapping("/precheck")
    public ResponseEntity<PrecheckResponse> precheck(@RequestParam String ip, @RequestParam String user) {
        var checks = List.of(
                service.confirmDnsHost(ip), service.ping(ip, 1500), service.tcpPort(ip, 22, 1500)
        );
        return ResponseEntity.ok(new PrecheckResponse(ip, user, checks));
    }

    // POST /api/precheck  (Content-Type: application/json)
    @PostMapping("/precheck")
    public ResponseEntity<PrecheckResponse> precheck(@RequestBody PrecheckRequest req) {
        var checks = new ArrayList<>(List.of(
                service.confirmDnsHost(req.ip),
                service.ping(req.ip, 1500),
                service.tcpPort(req.ip, 22, 1500)
        ));
        checks.add(service.osCheck(req.ip, req.user, req.password));
        return ResponseEntity.ok(new PrecheckResponse(req.ip, req.user, checks));
    }

    public static class PrecheckRequest {
        public String ip;
        public String user;
        public String password;
    }

}
