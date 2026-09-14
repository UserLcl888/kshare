package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_upload")
public class UserUpload {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String categoryName;
    private String groupName;
    private String fileName;
    private String contentMd;
    private String contentHtml;
    private Integer status;
    /** 处理状态：0=处理中 1=已完成 2=处理失败（图片搬运 + 正文渲染在后台线程池完成） */
    private Integer processStatus;
    private String adminReply;
    private LocalDateTime repliedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
