use std::io;
use rand::random_range;

fn main() {
    println!("Set min: ");
    let min: i32 = get_number(); 
    println!("Set max: ");
    let max: i32 = get_number(); 
    let randnum: i32 = random_range(min..=max);
    let mut input: i32 ;

    let mut uniquetries = 0;
    let mut numberstried: Vec<i32> = Vec::new();

    loop {
        println!("Enter: ");
        input = get_number();

        if !numberstried.contains(&input) {
            uniquetries += 1;         // Increment unique tries count
            numberstried.push(input);  // Optionally add the new input to the list
        }

        if input < randnum {
            println!("higher");
        } else if input > randnum {
            println!("lower");
        } else {
            println!("game over");
            println!("Unique tries: {uniquetries}");
            break;
        }
    }
}

fn get_number() -> i32{
    loop {
        let mut val: String = String::new();
        io::stdin().read_line(&mut val).unwrap();
        match val.trim().parse::<i32>() {
            Ok(num) => return num,
            Err(_) => println!("Please enter a number"), 
        }
    }
}