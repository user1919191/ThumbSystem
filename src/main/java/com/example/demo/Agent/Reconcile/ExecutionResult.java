package com.example.demo.Agent.Reconcile;

import java.util.Map;

public record ExecutionResult(String planId, String evidenceId, String action,
                              String status, String errorCode, String errorMessage,
                              Map<String, Object> detail, String consistencyStatus,
                              long costMs) {
}
