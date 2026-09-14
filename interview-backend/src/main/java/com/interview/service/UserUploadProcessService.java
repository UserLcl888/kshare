package com.interview.service;

import com.interview.entity.UserUpload;
import com.interview.mapper.UserUploadMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 用户投稿的异步处理：把正文里的图片搬到 MinIO（user-upload/）+ 渲染 HTML。
 *
 * <p>提交接口只做校验和落库（process_status=0）就立刻返回，这里用通用线程池
 * {@code appTaskExecutor}（见 config/AsyncConfig.java）在后台跑，跑完把状态改成
 * 1（已完成）或 2（处理失败）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserUploadProcessService {

    private final UserUploadMapper userUploadMapper;
    private final ContentRenderService contentRenderService;

    @Async("appTaskExecutor")
    public void process(Long uploadId, String rawMd) {
        try {
            ContentRenderService.RenderedContent rc = contentRenderService.render(rawMd, "user-upload");
            UserUpload done = new UserUpload();
            done.setId(uploadId);
            done.setContentMd(rc.contentMd());
            done.setContentHtml(rc.contentHtml());
            done.setProcessStatus(1);
            userUploadMapper.updateById(done);
            log.info("投稿异步处理完成 id={}", uploadId);
        } catch (Exception e) {
            log.error("投稿异步处理失败 id={}", uploadId, e);
            UserUpload fail = new UserUpload();
            fail.setId(uploadId);
            fail.setProcessStatus(2);
            userUploadMapper.updateById(fail);
        }
    }
}
