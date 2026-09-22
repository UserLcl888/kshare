package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 上传的附件（HTML 页面 / 文档）。
 * 只记录「文件存在哪、链接是什么」，正文里怎么用由管理员自己决定。
 */
@Data
@TableName("asset")
public class Asset {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** html=HTML 页面 file=文档 */
    private String kind;
    /** MinIO 对象名（如 html/202609/xxxx.html） */
    private String objectName;
    /** 对外访问地址，管理员复制到正文里的就是它 */
    private String url;
    /** 原始文件名（含中文） */
    private String fileName;
    /** 文件大小（字节） */
    private Long fileSize;
    /** 上传人（管理员ID） */
    private Long createdBy;
    private LocalDateTime createdAt;
}
