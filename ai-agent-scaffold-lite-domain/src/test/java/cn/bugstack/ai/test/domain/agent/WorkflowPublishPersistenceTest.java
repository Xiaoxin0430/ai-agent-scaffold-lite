package cn.bugstack.ai.test.domain.agent;

import cn.bugstack.ai.domain.agent.adapter.port.IPublishedWorkflowConfigPort;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentRegisterVO;
import cn.bugstack.ai.domain.agent.model.valobj.canvas.CanvasValidationResultVO;
import cn.bugstack.ai.domain.agent.model.valobj.canvas.WorkflowCanvasConversionResultVO;
import cn.bugstack.ai.domain.agent.model.valobj.canvas.WorkflowCanvasVO;
import cn.bugstack.ai.domain.agent.service.IArmoryService;
import cn.bugstack.ai.domain.agent.service.IWorkflowCanvasService;
import cn.bugstack.ai.domain.agent.service.IWorkflowPublishService;
import cn.bugstack.ai.domain.agent.service.armory.SpringWorkflowRuntimeRegistry;
import cn.bugstack.ai.domain.agent.service.canvas.WorkflowPublishService;
import cn.bugstack.ai.domain.agent.service.canvas.WorkflowRuntimeConfigFactory;
import cn.bugstack.ai.types.exception.AppException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.runner.InMemoryRunner;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.List;

import static org.mockito.Mockito.mock;

public class WorkflowPublishPersistenceTest {

    @Test
    public void committed_publish_stores_placeholder_and_registers_runner() throws Exception {
        try (Fixture fixture = new Fixture(false, false)) {
            Assert.assertTrue(fixture.publisher.publish(new WorkflowCanvasVO()).published());
            Assert.assertEquals("${TEST_CANVAS_KEY:}", fixture.repository.committed.getModule().getAiApi().getApiKey());
            Assert.assertNotNull(fixture.context.getBean("wf_new", AiAgentRegisterVO.class).getRunner());
        }
    }

    @Test
    public void assembly_failure_rolls_back_database_insert() throws Exception {
        try (Fixture fixture = new Fixture(true, false)) {
            try {
                fixture.publisher.publish(new WorkflowCanvasVO());
                Assert.fail("Expected assembly failure");
            } catch (Exception expected) {
                Assert.assertNull(fixture.repository.committed);
                Assert.assertFalse(fixture.context.containsBean("wf_new"));
            }
        }
    }

    @Test
    public void commit_failure_unregisters_this_runner() throws Exception {
        try (Fixture fixture = new Fixture(false, true)) {
            try {
                fixture.publisher.publish(new WorkflowCanvasVO());
                Assert.fail("Expected commit failure");
            } catch (Exception expected) {
                Assert.assertNull(fixture.repository.committed);
                Assert.assertFalse(fixture.context.containsBean("wf_new"));
            }
        }
    }

    @Test
    public void duplicate_database_id_does_not_register_runner() throws Exception {
        try (Fixture fixture = new Fixture(false, false)) {
            fixture.repository.duplicate = true;
            try {
                fixture.publisher.publish(new WorkflowCanvasVO());
                Assert.fail("Expected duplicate workflow ID");
            } catch (AppException expected) {
                Assert.assertEquals("0004", expected.getCode());
                Assert.assertFalse(fixture.context.containsBean("wf_new"));
            }
        }
    }

    private static class Fixture implements AutoCloseable {
        private final GenericApplicationContext context = new GenericApplicationContext();
        private final RecordingRepository repository = new RecordingRepository();
        private final IWorkflowPublishService publisher;

        private Fixture(boolean failAssembly, boolean failCommit) {
            context.refresh();
            SpringWorkflowRuntimeRegistry registry = new SpringWorkflowRuntimeRegistry();
            ReflectionTestUtils.setField(registry, "applicationContext", context);
            AiAgentConfigTableVO config = config();
            IWorkflowCanvasService canvasService = canvas -> new WorkflowCanvasConversionResultVO(
                    new CanvasValidationResultVO(List.of(), List.of("n1")), config);
            IArmoryService armory = tables -> {
                Assert.assertEquals("resolved-key", tables.get(0).getModule().getAiApi().getApiKey());
                if (failAssembly) throw new Exception("assembly failed");
                registry.registerIfAbsent("wf_new", AiAgentRegisterVO.builder()
                        .workflowId("wf_new").runner(mock(InMemoryRunner.class)).build());
            };
            WorkflowRuntimeConfigFactory runtimeFactory = new WorkflowRuntimeConfigFactory();
            ReflectionTestUtils.setField(runtimeFactory, "objectMapper", new ObjectMapper());
            ReflectionTestUtils.setField(runtimeFactory, "environment",
                    new MockEnvironment().withProperty("TEST_CANVAS_KEY", "resolved-key"));
            WorkflowPublishService service = new WorkflowPublishService();
            ReflectionTestUtils.setField(service, "canvasService", canvasService);
            ReflectionTestUtils.setField(service, "armoryService", armory);
            ReflectionTestUtils.setField(service, "applicationContext", context);
            ReflectionTestUtils.setField(service, "runtimeConfigFactory", runtimeFactory);
            ReflectionTestUtils.setField(service, "publishedWorkflowConfigPort", repository);
            ReflectionTestUtils.setField(service, "workflowRuntimeRegistry", registry);

            RecordingTransactionManager transactions = new RecordingTransactionManager(repository, failCommit);
            TransactionInterceptor interceptor = new TransactionInterceptor(
                    transactions, new AnnotationTransactionAttributeSource());
            ProxyFactory proxyFactory = new ProxyFactory(service);
            proxyFactory.addAdvice(interceptor);
            publisher = (IWorkflowPublishService) proxyFactory.getProxy();
        }

        @Override
        public void close() {
            context.close();
        }
    }

    private static AiAgentConfigTableVO config() {
        AiAgentConfigTableVO config = new AiAgentConfigTableVO();
        AiAgentConfigTableVO.Workflow workflow = new AiAgentConfigTableVO.Workflow();
        workflow.setId("wf_new");
        config.setWorkflow(workflow);
        AiAgentConfigTableVO.Module module = new AiAgentConfigTableVO.Module();
        AiAgentConfigTableVO.Module.AiApi api = new AiAgentConfigTableVO.Module.AiApi();
        api.setApiKey("${TEST_CANVAS_KEY:}");
        module.setAiApi(api);
        config.setModule(module);
        return config;
    }

    private static class RecordingRepository implements IPublishedWorkflowConfigPort {
        private AiAgentConfigTableVO pending;
        private AiAgentConfigTableVO committed;
        private boolean duplicate;

        @Override
        public void insert(AiAgentConfigTableVO config) {
            if (duplicate) throw new DuplicateKeyException("duplicate workflow_id");
            pending = config;
        }

        @Override
        public List<AiAgentConfigTableVO> findAll() {
            return committed == null ? List.of() : List.of(committed);
        }
    }

    private static class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private final RecordingRepository repository;
        private final boolean failCommit;

        private RecordingTransactionManager(RecordingRepository repository, boolean failCommit) {
            this.repository = repository;
            this.failCommit = failCommit;
        }

        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) {
            if (failCommit) throw new IllegalStateException("commit failed");
            repository.committed = repository.pending;
        }
        @Override protected void doRollback(DefaultTransactionStatus status) { repository.pending = null; }
    }
}
