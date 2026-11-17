package com.nexus.crm.modules;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Who may create, change or archive records in a workspace. The workspace comes from the {@code kind} path variable.
 * <ul>
 * <li>Products and automations: admins only.
 * <li>Campaigns: managers and admins.
 * <li>Quotes and communications: everyone who is signed in.
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasRole('ADMIN') or (!{'products', 'automations'}.contains(#kind) and (#kind != 'campaigns' or hasRole('MANAGER')))")
@interface CanWriteWorkspace {

}
