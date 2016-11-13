all: http/src/main/resources/keystore.jks http/src/test/resources/hyponome.pem

http/src/main/resources http/src/test/resources:
	mkdir -p $@

http/src/main/resources/keystore.jks: http/src/main/resources
	keytool -genkey \
		-alias domain \
		-keyalg RSA \
		-validity 365 \
		-keystore http/src/main/resources/keystore.jks \
		-keypass password \
		-storepass password \
		-dname "CN=localhost, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown"

http/src/test/resources/hyponome.pem: http/src/main/resources/keystore.jks http/src/test/resources
	keytool -exportcert \
		-rfc \
		-alias domain \
		-keystore http/src/main/resources/keystore.jks \
		-file http/src/test/resources/hyponome.pem \
		-storepass password \

clean:
	rm -rf store
	rm -f *.db

distclean: clean
	rm -f http/src/main/resources/keystore.jks
	rm -f http/src/test/resources/hyponome.pem

.PHONY: all clean distclean
