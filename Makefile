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
	@if [ -n "$(PORT)" ]; then $(JAR) -cp $(OUT_DIR) Main server $(PORT); else $(JAR) -cp $(OUT_DIR) Main server; fi

run-client: compile
	@if [ -n "$(HOST)" ] && [ -n "$(PORT)" ]; then $(JAR) -cp $(OUT_DIR) Main client $(HOST) $(PORT); \
	elif [ -n "$(HOST)" ]; then $(JAR) -cp $(OUT_DIR) Main client $(HOST); \
	elif [ -n "$(PORT)" ]; then $(JAR) -cp $(OUT_DIR) Main client $(PORT); \
	else $(JAR) -cp $(OUT_DIR) Main client; fi

clean:
	@rm -rf $(OUT_DIR)
	@find $(SRC_DIR) -name '*.class' -delete
	@echo "Build artifacts removed."
