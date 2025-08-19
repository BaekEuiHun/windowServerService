package InnotiumProject1.demo.precheck.service;

import InnotiumProject1.demo.precheck.dto.CheckResult;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;


@Service
public class PrecheckService {
    // DNS/호스트 확인 메서드
    public CheckResult confirmDnsHost(String host) {
        Instant t0 = Instant.now(); // 시작 시간 기록
        try {
            InetAddress addr = InetAddress.getByName(host); //url입력된 ip를 InetAddress 객체로 변환 (문자열 -> ip)
            Duration d = Duration.between(t0, Instant.now()); // 경과 시간
            return new CheckResult("DNS/호스트 확인", true, addr.getHostAddress(), d.toMillis());
            //addr.getHostAddress() -> 변환된 io를 String으로 추출
        } catch (UnknownHostException e) {
            Duration d = Duration.between(t0, Instant.now());
            return new CheckResult("DNS/호스트 확인", false, "호스트를 찾을 수 없음 : " + e.getMessage(), d.toMillis());
        }


    }

    public CheckResult ping(String host, int timeoutMs) {
        Instant t0 = Instant.now();
        try {
            boolean ok = InetAddress.getByName(host).isReachable(timeoutMs);
            Duration d = Duration.between(t0, Instant.now());
            return new CheckResult("핑(ICMP)", ok,
                    ok ? "응답 있음" : "응답 없음", d.toMillis());
        } catch (IOException e) {
            Duration d = Duration.between(t0, Instant.now());
            return new CheckResult("핑(icmp)", false, "핑 실패 :" + e.getMessage(), d.toMillis());
        }
    }

    public CheckResult tcpPort(String host, int port, int timeoutMs) {
        Instant t0 = Instant.now();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            Duration d = Duration.between(t0, Instant.now());
            return new CheckResult("TCP 포트" + port, true, "연결성공", d.toMillis());
        } catch (IOException e) {
            Duration d = Duration.between(t0, Instant.now());
            return new CheckResult("TCP 포트" + port, false, "연결실패 : " + e.getMessage(), d.toMillis());
        }

    }

}
