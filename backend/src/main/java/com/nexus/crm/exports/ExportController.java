package com.nexus.crm.exports;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexus.crm.identity.user.UserService;
import com.nexus.crm.mail.MailBannerService;
import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;

/**
 * Turns rows the browser already holds into a branded Excel file. The server adds the company
 * logo and the styling, so every export looks the same whichever screen it came from.
 * It reads no CRM data itself: a user can only export what they were already shown.
 */
@RestController
@RequestMapping("/api/v1/exports")
@RequiredArgsConstructor
class ExportController {
  private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
  private static final int MAX_COLUMNS = 40, MAX_ROWS = 20_000;

  private final MailBannerService brand;
  private final UserService users;

  record SheetRequest(String title, List<String> header, List<List<Object>> rows) { }

  @PostMapping("/sheet")
  ResponseEntity<byte[]> sheet(@AuthenticationPrincipal CurrentUser user, @RequestBody SheetRequest request) {
    if (request.header() == null || request.header().isEmpty() || request.header().size() > MAX_COLUMNS || request.header().contains(null))
      throw invalid("An export needs between 1 and " + MAX_COLUMNS + " columns.");
    List<List<Object>> rows = request.rows() == null ? List.of() : request.rows();
    if (rows.size() > MAX_ROWS || rows.contains(null)) throw invalid("An export can hold at most " + MAX_ROWS + " rows. Narrow the filters and try again.");
    String title = request.title() == null || request.title().isBlank() ? "Export" : request.title().strip();
    if (title.length() > 120) title = title.substring(0, 120);

    var company = brand.brand();
    String subtitle = (company.companyName() == null ? "" : company.companyName() + " · ") + "Exported " + LocalDate.now().format(DAY) + " by "
      + users.get(user.id()).getDisplayName() + " · " + rows.size() + (rows.size() == 1 ? " row" : " rows");
    byte[] file = SheetWriter.write(title, subtitle, request.header(), rows, company.logoPng());
    return ResponseEntity.ok().contentType(XLSX)
      .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("export.xlsx").build().toString())
      .body(file);
  }

  private ApiException invalid(String message) { return new ApiException(ErrorCode.VALIDATION_FAILED, message); }
}
