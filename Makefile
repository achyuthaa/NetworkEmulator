# Define the Java compiler
JAVAC = javac

# Define the source files
SOURCES = Bridge.java Station.java

# Define the target classes
CLASSES = $(SOURCES:.java=.class)

# Default target: compile all classes
all: $(CLASSES)

# Compile Java source files into class files
%.class: %.java
	$(JAVAC) $<

# Target to clean generated files
clean:
	rm -f $(CLASSES)

# Phony targets
.PHONY: all makeclean
