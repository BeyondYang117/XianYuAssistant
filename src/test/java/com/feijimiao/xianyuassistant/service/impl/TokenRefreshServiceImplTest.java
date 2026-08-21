package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.mapper.XianyuCookieMapper;
import com.feijimiao.xianyuassistant.service.OperationLogService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TokenRefreshServiceImplTest {

    @Test
    void mh5tkFailureAfterSuccessfulHasLoginDoesNotExpireCookie() {
        TokenRefreshServiceImpl service = new TokenRefreshServiceImpl();
        XianyuCookieMapper cookieMapper = mock(XianyuCookieMapper.class);
        OperationLogService operationLogService = mock(OperationLogService.class);
        ReflectionTestUtils.setField(service, "cookieMapper", cookieMapper);
        ReflectionTestUtils.setField(service, "operationLogService", operationLogService);

        Boolean result = ReflectionTestUtils.invokeMethod(
                service, "handleMh5tkRefreshFailure", 1L, 2, true, "no token");

        assertFalse(Boolean.TRUE.equals(result));
        verify(cookieMapper, never()).update(any(), any());
    }
}
