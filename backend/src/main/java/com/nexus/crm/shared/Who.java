package com.nexus.crm.shared;

/** Who or what a timeline entry or follow-up is about: a person (or deal) and the company they belong to. */
public record Who(String name, String company) {
}
