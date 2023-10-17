package com.example.api.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 请求结果类
 *
 * @param <T> 数据体类型
 */
@Data
public class Result<T> implements Serializable {

    /**
     * 消息
     */
    private String message;

    /**
     * 是否操作成功
     */
    private int code;

    /**
     * 返回的数据主体（返回的内容）
     */
    private T data;

    /**
     * 设定结果为成功
     *
     * @param msg 消息
     */
    public void setResultSuccess(String msg) {
        this.message = msg;
        this.code = 0;
        this.data = null;
    }

    /**
     * 设定结果为成功
     *
     * @param msg  消息
     * @param data 数据体
     */
    public void setResultSuccess(String msg, T data) {
        this.message = msg;
        this.code = 200;
        this.data = data;
    }

    /**
     * Set failed result
     *
     * @param msg message
     */

    public void setResultFailed(String msg) {
        this.message = msg;
        this.data = null;
    }

}
