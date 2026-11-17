package com.nexus.crm.modules;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Who may create, change or archive records in a workspace (the {@code kind} path variable).
 * Products and automations: admins. Campaigns: managers and admins. Quotes and communications: everyone.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasRole('ADMIN') or (!{'products', 'automations'}.contains(#kind) and (#kind != 'campaigns' or hasRole('MANAGER')))")
@interface CanWriteWorkspace {

}
