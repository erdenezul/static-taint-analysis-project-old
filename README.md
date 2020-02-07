# Program Analysis cource project


## Checklist

| test id            | description                          | ok?      |
| ------------------ | ------------------------------------ | -------- |
| test_1/one.js      | simple poc test case                 | yes      |
| test_2/two.js      | two function with objectlit          | yes      |
| test_3/three.js    | source after sink                    | yes      |
| test_4/four.js     | variable depend source in else       | yes      |
| test_5/five.js     | variable defined in case and reached sink | yes |
| test_6/six.js      | variable depends objectlit and push method | yes |


## How to run

```shell
java -jar target/closure-compiler-1.0-SNAPSHOT.jar ../benchmark/test_3/three.js
```

## Output

Taint analysis output should be written in same directory suffixed with _out.json
