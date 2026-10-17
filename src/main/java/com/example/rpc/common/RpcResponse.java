package com.example.rpc.common;

import java.io.Serializable;

/**
 * RPC响应包：存返回结果 / 异常
 */
public class RpcResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private Object result;
    private Exception exception;

    public Object getResult() { return result; }
    public void setResult(Object result) { this.result = result; }
    public Exception getException() { return exception; }
    public void setException(Exception exception) { this.exception = exception; }
}
