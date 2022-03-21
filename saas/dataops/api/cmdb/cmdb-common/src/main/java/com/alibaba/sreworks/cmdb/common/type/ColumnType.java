package com.alibaba.sreworks.cmdb.common.type;

import org.apache.commons.lang3.StringUtils;

public enum ColumnType {

    BOOLEAN("BOOLEAN"),
    INTEGER("INTEGER"),
    LONG("LONG"),
    FLOAT("FLOAT"),
    DOUBLE("DOUBLE"),
    STRING("STRING"),
    DATE("DATE"),
    ARRAY("ARRAY"),
    OBJECT("OBJECT");
//    NESTED("NESTED");

    private String type;


    ColumnType(String type) {
        this.type = type;
    }


    public static boolean contains(java.lang.String value) {
        for (ColumnType c : ColumnType.values()) {
            if (c.name().equals(value)) {
                return true;
            }
        }
        return false;
    }


    public String getType() {
        return type;
    }

    public static String toFormatString() {
        String result = StringUtils.join(ColumnType.values(), ",");
        return "[" + result + "]";
    }

}