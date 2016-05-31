#!/bin/bash

java -cp .:gatein-pages-export-1.0.0.jar:lib/exo.portal.component.common-5.2.2.GA.jar:lib/exo.portal.component.portal-5.2.2.GA.jar:lib/gatein-management-api-1.0.1-GA.jar:lib/jibx-run-1.2.2.jar:lib/mop-api-1.1.2-GA.jar:lib/mop-core-1.1.2-GA.jar:lib/ojdbc6.jar:lib/staxnav.core-0.9.6.jar com.example.PagesExportUtil > output.log 2>&1
