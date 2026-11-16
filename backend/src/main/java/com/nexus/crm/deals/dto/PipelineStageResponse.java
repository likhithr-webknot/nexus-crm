package com.nexus.crm.deals.dto;

import com.nexus.crm.deals.PipelineStage;

public record PipelineStageResponse(
	String code,
	String name,
	int position,
	PipelineStage.Kind kind,
	int winProbability
) {

	public static PipelineStageResponse from(PipelineStage stage) {
		return new PipelineStageResponse(stage.getCode(), stage.getName(), stage.getPosition(), stage.getKind(),
				stage.getWinProbability());
	}

}
