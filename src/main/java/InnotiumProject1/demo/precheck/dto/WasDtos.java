package InnotiumProject1.demo.precheck.dto;

import java.util.ArrayList;
import java.util.List;

public class WasDtos {


    /** 파일 업로드용 (멀티파트)에서 쓸 폼 파라미터 */
    public static class UploadForm {
        public String ip;
        public String username;
        public String password;
        /** 원격에 저장할 경로 */
        public String remotePath = "/WAS.tar";
    }

    /** 설치/압축해제 요청(JSON) */
    public static class SetupRequest {
        public String ip;
        public String username;
        public String password;

        /** 업로드된 tar 경로 */
        public String wasTarPath = "/WAS.tar";
        /** 압축 해제 위치 (기본 루트) */
        public String extractTo = "/";

        /** 반드시 설치할 패키지 목록(비우면 기본 세트 사용) */
        public List<String> packages;

        /** 기본 세트 (Ubuntu/Rocky 공통명) */
        public List<String> getPackagesOrDefault() {
            if (packages != null && !packages.isEmpty()) return packages;
            List<String> def = new ArrayList<>();
            // 가급적 공통 명칭 위주. (Rocky에서 cronolog/varnish는 EPEL 필요할 수 있음)
            def.add("gcc"); def.add("tcl"); def.add("net-tools"); def.add("unzip");
            def.add("policycoreutils"); def.add("vim"); def.add("openssh-server");
            // 웹/캐시/방화벽
            def.add("nginx"); def.add("varnish"); def.add("firewalld");
            // 선택(있으면 설치): cronolog
            def.add("cronolog");
            return def;
        }
    }

    /** 각 단계 결과 */
    public static class StepResult {
        public String name;     // 단계 이름 (ex: 업로드, 압축 해제)
        public boolean ok;      // 성공 여부
        public String message;  // 실행 결과 메시지
        public long durationMs; // 실행 소요 시간(ms)

        public StepResult() {}
        public StepResult(String name, boolean ok, String message, long durationMs) {
            this.name = name; this.ok = ok; this.message = message; this.durationMs = durationMs;
        }
    }

    /** 전체 설치 과정 응답 */
    public static class SetupResponse {
        public String ip;
        public String user;
        public List<StepResult> results = new ArrayList<>();
        public SetupResponse(String ip, String user) { this.ip = ip; this.user = user; }
        public void add(StepResult r) { results.add(r); }
        public boolean allOk() { return results.stream().allMatch(x -> x.ok); }
    }
}
