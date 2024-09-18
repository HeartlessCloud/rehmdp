package org.example.service;

import org.example.dto.Result;

public interface IFollowService {
    public Result isFollow(Long id);

    public Result commonFollows(Long id);

    public Result follow(Long followUserId, Boolean isFollow);
}
