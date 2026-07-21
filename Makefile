SHELL := /bin/sh

SRC_DIR := src
OUT_DIR := out
JAVA := javac
JAR := java
ENCODING := UTF-8

JAVA_SOURCES := $(shell find $(SRC_DIR) -name '*.java' | sort)

.PHONY: all compile run-server run-client clean

all: compile

compile:
	@mkdir -p $(OUT_DIR)
	@$(JAVA) -encoding $(ENCODING) -d $(OUT_DIR) $(JAVA_SOURCES)
	@echo "Compiled into $(OUT_DIR)/"

run-server: compile
	@$(JAR) -cp $(OUT_DIR) Main server

run-client: compile
	@$(JAR) -cp $(OUT_DIR) Main client

clean:
	@rm -rf $(OUT_DIR)
	@find $(SRC_DIR) -name '*.class' -delete
	@echo "Build artifacts removed."
