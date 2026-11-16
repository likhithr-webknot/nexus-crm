package com.nexus.crm.shared.web;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * One page of a list, with the total so the frontend can show "1-8 of 120".
 *
 * <p>
 * The design asked for cursor paging, but the screens show page numbers and
 * totals, which cursors cannot do. At this data size (tens of thousands of
 * rows) page numbers are fast enough.
 */
public record PageResponse<T>(List<T> items, long total, int page, int size) {

	public static final int MAX_SIZE = 100;

	/** Page numbers start at 1 for the client. Spring starts at 0. */
	public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
		return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getTotalElements(),
				page.getNumber() + 1, page.getSize());
	}

	/** Keeps a client-supplied page size between 1 and {@link #MAX_SIZE}. */
	public static int clampSize(int size) {
		return Math.max(1, Math.min(size, MAX_SIZE));
	}

}
