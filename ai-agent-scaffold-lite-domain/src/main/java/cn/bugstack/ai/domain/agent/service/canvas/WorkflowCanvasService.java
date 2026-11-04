package cn.bugstack.ai.domain.agent.service.canvas;

import cn.bugstack.ai.domain.agent.model.valobj.canvas.WorkflowCanvasConversionResultVO;
import cn.bugstack.ai.domain.agent.model.valobj.canvas.WorkflowCanvasVO;
import cn.bugstack.ai.domain.agent.service.IWorkflowCanvasService;
import cn.bugstack.ai.domain.agent.service.canvas.validation.SequentialWorkflowCanvasValidator;
import org.springframework.stereotype.Service;

@Service
public class WorkflowCanvasService implements IWorkflowCanvasService {
    private final SequentialWorkflowCanvasValidator validator;
    private final WorkflowCanvasConverter converter;

    public WorkflowCanvasService(SequentialWorkflowCanvasValidator validator, WorkflowCanvasConverter converter) {
        this.validator = validator;
        this.converter = converter;
    }

    @Override
    public WorkflowCanvasConversionResultVO validateAndConvert(WorkflowCanvasVO canvas) {
        var validation = validator.validate(canvas);
        if (!validation.valid())
            return new WorkflowCanvasConversionResultVO(validation, null);
        return new WorkflowCanvasConversionResultVO(validation, converter.convert(canvas, validation));
    }
}
