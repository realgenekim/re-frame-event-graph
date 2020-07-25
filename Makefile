run:
	clj -m main

cljtest:
	clj -A:test

clj-autotest:
	clj -A:test --watch

clj-testdefault:
	clj -A:test-default
