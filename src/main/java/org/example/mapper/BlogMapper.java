package org.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.example.entity.Blog;

// 由于有同位置自动绑定，所以甚至都不需要加上Mapper注解
public interface BlogMapper extends BaseMapper<Blog> {

}
