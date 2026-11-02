package art.pilipala.api.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import art.pilipala.api.common.ErrorCodes;
import art.pilipala.api.project.infra.ProjectException;
import art.pilipala.api.project.support.InMemoryProjectStore;
import org.junit.jupiter.api.Test;

/**
 * 云端项目用例：创建、列表、详情、更新、删除，以及跨租户隔离与参数校验。
 *
 * <p>项目归属当前租户：{@code user_id} 只是操作者，同一租户里换设备也看得见。
 */
class ProjectServiceTest {

    private final InMemoryProjectStore store = new InMemoryProjectStore();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ProjectService service = new ProjectService(store, mapper);

    private final UUID owner = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final UUID ownerTenant = UUID.randomUUID();
    private final UUID otherTenant = UUID.randomUUID();

    @Test
    void createStoresTheSnapshotAndReturnsVersionOne() {
        ObjectNode snapshot = mapper.createObjectNode();
        snapshot.putArray("nodes");

        ProjectDetailView created = service.create(owner, ownerTenant, "我的画布", snapshot);

        assertThat(created.name()).isEqualTo("我的画布");
        assertThat(created.version()).isEqualTo(1);
        assertThat(created.canvasData().isObject()).isTrue();
        assertThat(UUID.fromString(created.id())).isNotNull();
    }

    @Test
    void createWithoutCanvasDataStoresAnEmptyObject() {
        ProjectDetailView created = service.create(owner, ownerTenant, "空画布", null);

        assertThat(created.canvasData().isObject()).isTrue();
        assertThat(created.canvasData().isEmpty()).isTrue();
    }

    @Test
    void listReturnsOnlyTheTenantsProjects() {
        service.create(owner, ownerTenant, "A", null);
        service.create(owner, ownerTenant, "B", null);
        service.create(other, otherTenant, "别人的", null);

        assertThat(service.list(ownerTenant)).hasSize(2);
        assertThat(service.list(ownerTenant).stream().map(ProjectSummaryView::name)).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    void getOfAnotherTenantProjectIsNotFound() {
        ProjectDetailView created = service.create(owner, ownerTenant, "A", null);

        assertThatThrownBy(() -> service.get(otherTenant, UUID.fromString(created.id())))
                .isInstanceOf(ProjectException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCodes.PROJECT_NOT_FOUND);
    }

    @Test
    void updateBumpsTheVersionAndKeepsTheUntouchedFields() {
        ProjectDetailView created = service.create(owner, ownerTenant, "A", null);
        UUID projectId = UUID.fromString(created.id());

        ProjectDetailView updated = service.update(ownerTenant, projectId, "改名", null);

        assertThat(updated.name()).isEqualTo("改名");
        assertThat(updated.version()).isEqualTo(2);
    }

    @Test
    void updateOfAnotherTenantProjectIsNotFound() {
        ProjectDetailView created = service.create(owner, ownerTenant, "A", null);

        assertThatThrownBy(() -> service.update(otherTenant, UUID.fromString(created.id()), "抢", null))
                .isInstanceOf(ProjectException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCodes.PROJECT_NOT_FOUND);
    }

    @Test
    void deleteOfAnotherTenantProjectIsNotFound() {
        ProjectDetailView created = service.create(owner, ownerTenant, "A", null);

        assertThatThrownBy(() -> service.delete(otherTenant, UUID.fromString(created.id())))
                .isInstanceOf(ProjectException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCodes.PROJECT_NOT_FOUND);
        assertThat(service.list(ownerTenant)).hasSize(1);
    }

    @Test
    void deleteRemovesTheProject() {
        ProjectDetailView created = service.create(owner, ownerTenant, "A", null);

        DeleteProjectView deleted = service.delete(ownerTenant, UUID.fromString(created.id()));

        assertThat(deleted.deleted()).isTrue();
        assertThat(deleted.id()).isEqualTo(created.id());
        assertThat(service.list(ownerTenant)).isEmpty();
    }

    @Test
    void blankOrTooLongNameIsRejected() {
        assertThatThrownBy(() -> service.create(owner, ownerTenant, "   ", null))
                .isInstanceOf(ProjectException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCodes.VALIDATION_ERROR);
        assertThatThrownBy(() -> service.create(owner, ownerTenant, "x".repeat(129), null))
                .isInstanceOf(ProjectException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCodes.VALIDATION_ERROR);
    }

    @Test
    void nonObjectCanvasDataIsRejected() {
        assertThatThrownBy(() -> service.create(owner, ownerTenant, "A", mapper.createArrayNode()))
                .isInstanceOf(ProjectException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCodes.VALIDATION_ERROR);
    }

    @Test
    void updateWithoutAnyFieldIsRejected() {
        ProjectDetailView created = service.create(owner, ownerTenant, "A", null);

        assertThatThrownBy(() -> service.update(ownerTenant, UUID.fromString(created.id()), null, null))
                .isInstanceOf(ProjectException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCodes.VALIDATION_ERROR);
    }
}
