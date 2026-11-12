package com.bakaroo.bingo.backend.feature.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bakaroo.bingo.backend.feature.agent.entity.AgentStatus;
import com.bakaroo.bingo.backend.feature.admin.service.AdminReadService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminReadControllerPaginationTest {

    private final AdminReadService service = mock(AdminReadService.class);
    private final AdminReadController controller = new AdminReadController(service);

    @Test
    void appliesDefaultPageSettingsAndKeepsAdminAndSupportAuthorization() {
        var expected = PageRequest.of(0, 20,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        when(service.agents(null, null, expected)).thenReturn(Page.empty(expected));

        controller.agents(null, null, 0, 20, null);

        verify(service).agents(null, null, expected);
        var authorization = AdminReadController.class.getAnnotation(PreAuthorize.class);
        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasAnyRole('ADMIN','SUPPORT')");
    }

    @Test
    void translatesExplicitSortAndRejectsInvalidPaginationAndDirection() {
        var expected = PageRequest.of(2, 50,
                Sort.by(Sort.Order.asc("city"), Sort.Order.desc("id")));
        when(service.agents("Addis", AgentStatus.PENDING, expected)).thenReturn(Page.empty(expected));

        controller.agents("Addis", AgentStatus.PENDING, 2, 50, List.of("city,asc", "id,desc"));

        verify(service).agents("Addis", AgentStatus.PENDING, expected);
        assertThatThrownBy(() -> controller.agents(null, null, -1, 20, null))
                .isInstanceOf(com.bakaroo.bingo.backend.common.exception.BadRequestException.class);
        assertThatThrownBy(() -> controller.agents(null, null, 0, 101, null))
                .isInstanceOf(com.bakaroo.bingo.backend.common.exception.BadRequestException.class);
        assertThatThrownBy(() -> controller.agents(null, null, 0, 20, List.of("city,sideways")))
                .isInstanceOf(com.bakaroo.bingo.backend.common.exception.BadRequestException.class);
    }
}
