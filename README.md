# org.openhab.binding.fio
openhab.cfg example:
```
######################## FIO Binding ###########################

# FIO token
fio:token={token}

# FIO refresh
fio:refresh=1800000
```

binding example:
```
String FioBalance "Fio account balance [%s]" <money> (FF_Working) { fio="{number}" }
```
replace {token} with your Fio account token

replace {number} with your Fio account number
