all: src/main/resources/keystore.jks src/test/resources/hyponome.pem

src/main/resources/keystore.jks:
	keytool -genkey \
		-alias domain \
		-keyalg RSA \
		-validity 365 \
		-keystore src/main/resources/keystore.jks \
		-keypass password \
		-storepass password \
		-dname "CN=localhost, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown"

src/test/resources/hyponome.pem: src/main/resources/keystore.jks
	keytool -exportcert \
		-rfc \
		-alias domain \
		-keystore src/main/resources/keystore.jks \
		-file src/test/resources/hyponome.pem \
		-storepass password \

clean:
	rm -f src/main/resources/keystore.jks
	rm -f src/test/resources/hyponome.pem

.PHONY: all clean
