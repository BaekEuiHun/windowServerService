package InnotiumProject1.demo.precheck.dto;

import java.util.List;

public record PrecheckResponse(String ip, String user, List<CheckResult> checks) {
}
