package com.nexus.crm.shared.limits;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;

/**
 * Refuses request bodies that are too big, before they are read into memory. Spring caps file uploads
 * (multipart) itself, so only JSON bodies are checked here.
 */
public class BodySizeLimitFilter extends OncePerRequestFilter {

	private static final long DEFAULT_LIMIT = 1_000_000;

	private final HandlerExceptionResolver errors;

	public BodySizeLimitFilter(HandlerExceptionResolver errors) {
		this.errors = errors;
	}

	/** Most bytes a JSON body may have on this path. */
	static long limitFor(String path) {
		return switch (path) {
			case "/api/v1/leads/imports", "/api/v1/exports/sheet" -> 16_000_000;
			case "/api/v1/mail/banner" -> 3_500_000; // an image sent as base64
			default -> DEFAULT_LIMIT;
		};
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String type = request.getContentType();
		if (type != null && type.toLowerCase().startsWith("multipart/")) {
			chain.doFilter(request, response);
			return;
		}
		long limit = limitFor(request.getRequestURI());
		if (request.getContentLengthLong() > limit) {
			this.errors.resolveException(request, response, null,
					new ApiException(ErrorCode.TOO_LARGE, "The request is too large."));
			return;
		}
		// A body sent in chunks has no length up front, so stop reading once it passes the limit.
		chain.doFilter(new LimitedRequest(request, limit), response);
	}

	private static final class LimitedRequest extends HttpServletRequestWrapper {

		private final long limit;

		LimitedRequest(HttpServletRequest request, long limit) {
			super(request);
			this.limit = limit;
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			ServletInputStream source = super.getInputStream();
			return new ServletInputStream() {

				private long read;

				@Override
				public int read() throws IOException {
					int value = source.read();
					if (value >= 0 && ++this.read > LimitedRequest.this.limit) {
						throw new IOException("Request body is too large");
					}
					return value;
				}

				@Override
				public int read(byte[] buffer, int offset, int length) throws IOException {
					int count = source.read(buffer, offset, length);
					if (count > 0 && (this.read += count) > LimitedRequest.this.limit) {
						throw new IOException("Request body is too large");
					}
					return count;
				}

				@Override
				public boolean isFinished() {
					return source.isFinished();
				}

				@Override
				public boolean isReady() {
					return source.isReady();
				}

				@Override
				public void setReadListener(ReadListener listener) {
					source.setReadListener(listener);
				}

			};
		}

	}

}
