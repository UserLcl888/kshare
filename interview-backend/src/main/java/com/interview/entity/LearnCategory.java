package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("learn_category")
public class LearnCategory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String slug;
    private Integer sortOrder;
    private String coverUrl;
    /** 封面缩略图 URL（列表页用，宽 800 的 WebP；空则回退 coverUrl） */
    private String coverThumbUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
