.PHONY: docs jar clean

jar: target/Flier.jar

target/Flier.jar: pom.xml src/*
	@echo "Building jar..."
	mvn package --batch-mode
	@echo "Jar built successfully"

docs: docs/Documentation.pdf

docs/Documentation.pdf: docs/*.md style.css
	@echo "Generating documentation..."
	pandoc \
		docs/Home.md \
		docs/Installation.md \
		docs/Commands.md \
		docs/Integrations.md \
		docs/Lobby.md \
		docs/Arena.md \
		docs/Game.md \
		docs/ItemSet.md \
		docs/Item.md \
		docs/Engine.md \
		docs/Wings.md \
		docs/Action.md \
		docs/Activator.md \
		docs/Bonus.md \
		docs/Modifications.md \
		docs/Effects.md \
		-f gfm -t html5 \
		-V margin-top=2cm -V margin-bottom=2cm -V margin-left=2cm -V margin-right=2cm \
		--css style.css \
		-o docs/Documentation.pdf
	@echo "Documentation generated successfully"

clean:
	@echo "Cleaning build artifacts..."
	rm -rf target/
	rm -f docs/Documentation.pdf
	@echo "Clean completed"
