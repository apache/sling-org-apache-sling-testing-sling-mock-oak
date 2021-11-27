[![Apache Sling](https://sling.apache.org/res/logos/sling.png)](https://sling.apache.org)

&#32;[![Build Status](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-testing-sling-mock-oak/job/master/badge/icon)](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-testing-sling-mock-oak/job/master/)&#32;[![Test Status](https://img.shields.io/jenkins/tests.svg?jobUrl=https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-testing-sling-mock-oak/job/master/)](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-testing-sling-mock-oak/job/master/test/?width=800&height=600)&#32;[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=apache_sling-org-apache-sling-testing-sling-mock-oak&metric=coverage)](https://sonarcloud.io/dashboard?id=apache_sling-org-apache-sling-testing-sling-mock-oak)&#32;[![Sonarcloud Status](https://sonarcloud.io/api/project_badges/measure?project=apache_sling-org-apache-sling-testing-sling-mock-oak&metric=alert_status)](https://sonarcloud.io/dashboard?id=apache_sling-org-apache-sling-testing-sling-mock-oak)&#32;[![JavaDoc](https://www.javadoc.io/badge/org.apache.sling/org.apache.sling.testing.sling-mock-oak.svg)](https://www.javadoc.io/doc/org.apache.sling/org-apache-sling-testing-sling-mock-oak)&#32;[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.sling/org.apache.sling.testing.sling-mock-oak/badge.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.apache.sling%22%20a%3A%22org.apache.sling.testing.sling-mock-oak%22)&#32;[![testing](https://sling.apache.org/badges/group-testing.svg)](https://github.com/apache/sling-aggregator/blob/master/docs/group/testing.md) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

# Apache Sling Testing Sling Mock Jackrabbit Oak-based Resource Resolver

This module is part of the [Apache Sling](https://sling.apache.org) project.

Implements a resource resolver type for [Jackrabbit Oak](https://jackrabbit.apache.org/oak) that can be used in unit tests based on Sling Mocks.

This is a separate Maven artifact because it introduces a lot of further dependencies. In order to prevent dependency version overrides of Oak bundles all necessary bundles are shaded into this JAR (and by that cannot be overwritten by consumers). Make sure to depend on this artifact prior to artifact `org.apache.sling.testing.sling-mock.*` in order to give the Oak classes in this JAR precedence in the Maven test class path.

Documentation:
<https://sling.apache.org/documentation/development/sling-mock.html>
