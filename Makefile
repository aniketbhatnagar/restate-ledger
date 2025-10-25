.PHONY: load-test load-test-down

load-test:
	@mkdir -p load-test/reports
	docker compose up --build

load-test-down:
	docker compose down
