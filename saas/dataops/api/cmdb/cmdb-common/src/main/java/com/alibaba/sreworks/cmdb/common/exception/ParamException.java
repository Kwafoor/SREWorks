package com.alibaba.sreworks.cmdb.common.exception;

public class ParamException extends Exception {
    public ParamException(String message) {
        super(message);
    }

    public ParamException(Throwable cause) {
        super(cause);
    }
}
