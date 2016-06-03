#!/bin/bash

java -cp .:gatein-pages-export-1.0.0-optimized.jar:lib/* com.example.PagesExportUtil > output.log 2>&1
