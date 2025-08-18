package InnotiumProject1.demo.controller;

import InnotiumProject1.demo.dto.PrecheckResponse;
import InnotiumProject1.demo.service.PrecheckService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

}
