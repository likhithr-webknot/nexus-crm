package com.nexus.crm.leads.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.nexus.crm.leads.LeadService;

public record LeadImportRequest(
	@NotNull @Size(max = LeadService.MAX_IMPORT_ROWS) List<LeadImportRow> rows
) {
}
