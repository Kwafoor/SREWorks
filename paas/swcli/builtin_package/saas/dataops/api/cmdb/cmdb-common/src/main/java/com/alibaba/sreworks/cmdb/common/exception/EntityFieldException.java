package com.alibaba.sreworks.cmdb.common.exception;

public class EntityFieldException extends Exception {
    public EntityFieldException(String message) {
        super(message);
    }

    public EntityFieldException(Throwable cause) {
        super(cause);
    }
}
