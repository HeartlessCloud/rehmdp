package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@TableName("tb_follow")
@Accessors(chain = true)
@EqualsAndHashCode
public class Follow {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /**
     * 点赞用户的id
     */
    private Long userId;
    /**
     * 被点赞用户的id
     */
    private Long followUserId;
    /**
     * 点赞的时间
     */
    private LocalDateTime createTime;

}
