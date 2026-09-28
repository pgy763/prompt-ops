package com.pgy.promptops.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把领域里的异常翻译成合适的 HTTP 状态码。
 * <p>
 * 没有做统一的响应包装体（那种 {@code {code,msg,data}} 结构）—— 这个项目是内部工具，
 * 直接用 HTTP 状态码 + 错误信息更简单，也更容易和 curl / 前端调试配合。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> onBadRequest(IllegalArgumentException e) {
        return body("BAD_REQUEST", e.getMessage());
    }

    /**
     * 「状态不对」类问题：版本还不存在、还没跑过评估、数据不一致。
     * 这类不是参数写错，而是「当前状态下不能这么做」，用 409 更准确。
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> onConflict(IllegalStateException e) {
        return body("CONFLICT", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> onUnexpected(Exception e) {
        return body("INTERNAL_ERROR", e.getClass().getSimpleName() + ": " + e.getMessage());
    }

    private Map<String, Object> body(String error, String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("error", error);
        map.put("message", message);
        return map;
    }
}
