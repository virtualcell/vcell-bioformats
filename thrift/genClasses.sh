#!/usr/bin/env bash

# Utility to (re-)generate Java classes from thrift IDL specifications

thrift --out ../src/main/java --gen java:generated_annotations=undated imageDataset.thrift
