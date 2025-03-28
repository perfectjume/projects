use std::io;
use rand::random_range;

fn main() {
    let randnum = random_range(1..=100);
    let mut input = String::new();

    loop {
        println!("Enter: ");
        input.clear();

        io::stdin().read_line(&mut input).unwrap();

        let guess: i32 = input.trim().parse().unwrap();

        if guess < randnum {
            println!("higher");
        } else if guess > randnum {
            println!("lower");
        } else {
            println!("gj");
            break;
        }
    }
}
