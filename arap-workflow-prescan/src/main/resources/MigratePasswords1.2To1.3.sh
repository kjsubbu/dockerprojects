#!/bin/bash

TMPFILE="/tmp/.${0##*/}-$$"
# Let's move the password from the Database in 1.2 release to the vault in 1.3 release
for i in $(psql -t -A -F":" -h 127.0.0.1 -d postgres -U postgres -c "select label, password from credentials WHERE NULLIF(password, '') IS NOT NULL"); do
  label=$( echo $i | cut -d: -f1 );
  pwd=$( echo $i | cut -d: -f2 );
  echo "$label $pwd" >> $TMPFILE
done

LIBDIR=/var/lib/cavirin/worklog-prescan
java -cp arap-db-1.0.0.jar:vault-java-driver-3.0.0.jar com.cavirin.arap.db.entities.configuration.VaultEncryption  $TMPFILE https://localhost:8200 52a81881-e444-73f6-c440-464091b65fc8

rm $TMPFILE


