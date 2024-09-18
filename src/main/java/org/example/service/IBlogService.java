package org.example.service;

import org.example.dto.Result;

public interface IBlogService {
    public Result likeBlog(Long id);

    public Result queryBlogLikes(Long id);
}
