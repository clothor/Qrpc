package com.example.rpc.common;

import java.io.Serializable;

/**
 * RPC请求包：存调用哪个接口、哪个方法、参数
 */
public class RpcRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String interfaceName;
    private String methodName;
    private String[] paramTypeNames;
    private Object[] params;
    private long requestId;

    public String getInterfaceName() { return interfaceName; }
    public void setInterfaceName(String interfaceName) { this.interfaceName = interfaceName; }
    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
    public String[] getParamTypeNames() { return paramTypeNames; }
    public void setParamTypeNames(String[] paramTypeNames) { this.paramTypeNames = paramTypeNames; }
    public Object[] getParams() { return params; }
    public void setParams(Object[] params) { this.params = params; }
    public long getRequestId() { return requestId; }
    public void setRequestId(long requestId) { this.requestId = requestId; }
}
