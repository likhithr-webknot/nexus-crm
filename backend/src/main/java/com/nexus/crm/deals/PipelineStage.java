package com.nexus.crm.deals;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** A step in the one fixed sales pipeline. The rows are added by a migration and the app only reads them. */
@Entity
@Table(name = "pipeline_stage")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PipelineStage {

	/** Whether a deal in this stage is still open, or finished as won or lost. */
	public enum Kind {

		OPEN, WON, LOST

	}

	/** Stage codes that the code needs to know about. */
	public static final String WON = "WON";

	public static final String LOST = "LOST";

	/** For example "PROPOSAL". */
	@Id
	private String code;

	@Column(nullable = false)
	private String name;

	/** The left-to-right order on the board, starting at 1. */
	@Column(nullable = false)
	private short position;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Kind kind;

	/** Percent, 0 to 100. Used for the weighted forecast. */
	@Column(name = "win_probability", nullable = false)
	private short winProbability;

}
